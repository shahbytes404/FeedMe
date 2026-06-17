import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PostComposer } from './post-composer';

describe('PostComposer', () => {
  let component: PostComposer;
  let fixture: ComponentFixture<PostComposer>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PostComposer],
    }).compileComponents();

    fixture = TestBed.createComponent(PostComposer);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
